using System;
using System.Collections.Generic;
using System.IO;

namespace ExplorViz.Code.Analysis.Fixtures
{
    // Single-line comment

    /* Single-line block comment */

    /*
     * Multi-line
     * block comment
     */

    /// <summary>
    /// Comprehensive C# parser test input for regression testing.
    /// </summary>
    [Obsolete]
    public class ParserTestInput : AbstractBase, ISerializableMarker, IRunnable, ICallable
    {
        /// <summary>Field documentation comment.</summary>
        public const int PublicStaticFinalInt = 42;

        private static volatile bool privateStaticVolatileFlag;

        protected string protectedTransientField;

        readonly double[] finalArrayField = new double[] { 1.0, 2.0 };

        private Dictionary<string, List<int>> nestedGenericField;

        public string Name { get; set; }

        /// <summary>No-arg constructor.</summary>
        public ParserTestInput() : base(0)
        {
            this.nestedGenericField = new Dictionary<string, List<int>>();
        }

        /// <summary>Constructor with parameters.</summary>
        protected ParserTestInput(string name, params int[] values)
            : base(values == null ? 0 : values.Length)
        {
            this.protectedTransientField = name;
        }

        public void Run()
        {
            if (PublicStaticFinalInt <= 0)
            {
                throw new InvalidOperationException("constant must be positive");
            }
        }

        public string Call()
        {
            return DoWork(true, 1, 2, 'x', 3, 4L, 5.0f, 6.0d, null) ? "ok" : "no";
        }

        public T GenericMethod<T>(T value) where T : struct
        {
            return value;
        }

        protected bool DoWork(
            bool flag, byte b, short s, char c, int i, long l, float f, double d, object obj)
        {
            if (flag) { return true; }
            if (obj is string) { return false; }

            switch (i)
            {
                case 0:
                    break;
                case 1:
                    for (int n = 0; n < 3; n++)
                    {
                        if (n == 1) { continue; }
                        if (n == 2) { break; }
                    }
                    break;
                default:
                    while (s > 0) { s--; }
                    do { b++; } while (b < 3);
                    break;
            }

            try
            {
                if (l < 0L) { throw new IOException("negative"); }
            }
            catch (IOException)
            {
                throw;
            }
            finally
            {
                privateStaticVolatileFlag = Math.Abs(i) > 0;
            }

            nestedGenericField["key"] = new List<int>();
            return f > 0.0f && d > 0.0d && c != '\0';
        }

        public override int AbstractHook()
        {
            return GetBaseValue();
        }

        /// <summary>Public nested class.</summary>
        public sealed class StaticNested
        {
            private int nestedField;

            public StaticNested(int nestedField)
            {
                this.nestedField = nestedField;
            }

            public int GetNestedField()
            {
                return nestedField;
            }
        }

        /// <summary>Nested interface.</summary>
        public interface NestedInterface : ISerializableMarker
        {
            void AbstractInterfaceMethod();
        }

        /// <summary>Nested enum.</summary>
        public enum NestedEnum
        {
            Alpha,
            Beta,
            Gamma
        }
    }

    /// <summary>Abstract base class.</summary>
    public abstract class AbstractBase
    {
        private readonly int baseValue;

        protected AbstractBase(int baseValue)
        {
            this.baseValue = baseValue;
        }

        public int GetBaseValue()
        {
            return baseValue;
        }

        public abstract int AbstractHook();
    }

    public interface ISerializableMarker { void Mark(); }
    public interface IRunnable { void Run(); }
    public interface ICallable { string Call(); }

    public enum TopLevelEnum { One, Two, Three }

    public static class ModuleHelper
    {
        public static void PackagePrivateStaticMethod() { }
    }
}
