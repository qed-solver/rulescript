package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/rulescript"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

var nativeRootDifferences = map[string]string{
	"EliminateDistinctSetLeft":       "RuleScript uses GroupBy and native uses DistinctOn",
	"EliminateJoinUnderGroupByLeft":  "RuleScript removes the identity Project under GroupBy",
	"EliminateJoinUnderGroupByRight": "RuleScript removes the identity Project under GroupBy",
	"EliminateZeroCardSelect":        "RuleScript removes Select and native retains it",
	"PruneJoinLeftCols":              "RuleScript inserts Project and native narrows Scan",
	"PruneJoinRightCols":             "RuleScript inserts Project and native narrows Scan",
	"PruneProjectCols":               "RuleScript inlines the computed column and native retains the inner Project",
	"PruneSelectCols":                "RuleScript inserts Project and native narrows Scan",
	"PruneSemiAntiJoinRightCols":     "RuleScript inserts Project and native narrows Scan",
	"PushAggDistinctIntoGroupBy":     "RuleScript models DistinctOn as GroupBy",
}

var nativeAlphaColumns = map[string]int{
	"ExtractJoinComparisons": 2,
}

func TestMatchesNativeRules(t *testing.T) {
	t.Run("EliminateSemiJoin", TestEliminateSemiJoin)
	t.Run("SimplifyLeftJoin", TestSimplifyLeftJoin)
	t.Run("SimplifyRightJoin", TestSimplifyRightJoin)
	t.Run("EliminateJoinUnderProjectLeft", TestEliminateJoinUnderProjectLeft)
	t.Run("EliminateJoinUnderProjectRight", TestEliminateJoinUnderProjectRight)
	t.Run("EliminateJoinUnderGroupByRight", TestEliminateJoinUnderGroupByRight)
	t.Run("FoldGroupByAndWindow", TestFoldGroupByAndWindow)
	t.Run("PruneWindowInputCols", TestPruneWindowInputCols)
	t.Run("PruneWindowOutputCols", TestPruneWindowOutputCols)
	t.Run("PushSelectIntoWindow", TestPushSelectIntoWindow)
	t.Run("TryDecorrelateWindow", TestTryDecorrelateWindow)
	t.Run("ExtractJoinComparisons", TestExtractJoinComparisons)
	t.Run("PushAggDistinctIntoGroupBy", TestPushAggDistinctIntoGroupBy)
	t.Run("EliminateDistinctSetLeft", TestEliminateDistinctSetLeft)
	t.Run("EliminateDistinctSetRight", TestEliminateDistinctSetRight)
	t.Run("EliminateExistsGroupBy", TestEliminateExistsGroupBy)
	t.Run("EliminateJoinUnderGroupByLeft", TestEliminateJoinUnderGroupByLeft)
	t.Run("EliminateRedundantBarrier", TestEliminateRedundantBarrier)
	t.Run("EliminateSetLeft", TestEliminateSetLeft)
	t.Run("EliminateSetRight", TestEliminateSetRight)
	t.Run("EliminateZeroCardProject", TestEliminateZeroCardProject)
	t.Run("EliminateZeroCardSelect", TestEliminateZeroCardSelect)
	t.Run("FoldColumnAccess", TestFoldColumnAccess)
	t.Run("FoldGroupingOperators", TestFoldGroupingOperators)
	t.Run("FoldIndirection", TestFoldIndirection)
	t.Run("FoldNotInEmpty", TestFoldNotInEmpty)
	t.Run("PruneExplainCols", TestPruneExplainCols)
	t.Run("PruneGroupByCols", TestPruneGroupByCols)
	t.Run("PruneJoinLeftCols", TestPruneJoinLeftCols)
	t.Run("PruneJoinRightCols", TestPruneJoinRightCols)
	t.Run("PruneMutationInputCols", TestPruneMutationInputCols)
	t.Run("PruneOrdinalityCols", TestPruneOrdinalityCols)
	t.Run("PruneProjectCols", TestPruneProjectCols)
	t.Run("PruneProjectSetCols", TestPruneProjectSetCols)
	t.Run("PruneSelectCols", TestPruneSelectCols)
	t.Run("PruneSemiAntiJoinRightCols", TestPruneSemiAntiJoinRightCols)
	t.Run("PruneWithScanCols", TestPruneWithScanCols)
	t.Run("PushLeakproofJoinIntoPermeableBarrierLeft", TestPushLeakproofJoinIntoPermeableBarrierLeft)
	t.Run("PushLeakproofJoinIntoPermeableBarrierRight", TestPushLeakproofJoinIntoPermeableBarrierRight)
	t.Run("PushLeakproofProjectionsIntoPermeableBarrier", TestPushLeakproofProjectionsIntoPermeableBarrier)
	t.Run("PushSelectIntoOrdinality", TestPushSelectIntoOrdinality)
	t.Run("RemoveZeroCardLock", TestRemoveZeroCardLock)
	t.Run("SimplifyZeroCardinalitySemiJoin", TestSimplifyZeroCardinalitySemiJoin)
	prune := func(r opt.RuleName) bool {
		return r == opt.EliminateProject || strings.HasPrefix(r.String(), "Prune")
	}
	only := func(names ...string) func(opt.RuleName) bool {
		return func(r opt.RuleName) bool {
			for _, n := range names {
				if r.String() == n {
					return true
				}
			}
			return false
		}
	}
	either := func(a, b func(opt.RuleName) bool) func(opt.RuleName) bool {
		return func(r opt.RuleName) bool { return a(r) || b(r) }
	}
	cases := []struct {
		rule   string
		native func(opt.RuleName) bool
		helper func(opt.RuleName) bool
		sqls   []string

		expectDifference bool
	}{
		{rule: "EliminateExistsProject", native: only("EliminateExistsProject"), sqls: []string{"SELECT x FROM u WHERE EXISTS (SELECT -a FROM t)"}},
		{rule: "HoistUnboundFilterFromExistsSubquery", native: only("HoistUnboundFilterFromExistsSubquery"), sqls: []string{"SELECT x FROM u WHERE EXISTS (SELECT * FROM (VALUES (1),(2)) AS s(a) WHERE random()>0 AND a>0)"}, expectDifference: true},
		{rule: "HoistUnboundJoinFilterFromExistsSubquery", native: only("HoistUnboundJoinFilterFromExistsSubquery"), sqls: []string{"SELECT a FROM nn WHERE EXISTS (SELECT * FROM (VALUES (1)) AS l(x) JOIN (VALUES (1)) AS r(y) ON random()>0 AND x=y)"}, expectDifference: true},
		{rule: "HoistSelectExists", native: only("HoistSelectExists"), sqls: []string{"SELECT x FROM u WHERE EXISTS (SELECT * FROM (VALUES (1),(2)) AS s(a) WHERE a>0) AND x>0"}, expectDifference: true},
		{rule: "HoistSelectNotExists", native: only("HoistSelectNotExists"), sqls: []string{"SELECT x FROM u WHERE NOT EXISTS (SELECT * FROM (VALUES (1),(2)) AS s(a) WHERE a>0) AND x>0"}, expectDifference: true},
		{rule: "HoistJoinSubquery", native: only("HoistJoinSubquery"), sqls: []string{"SELECT * FROM u JOIN t ON u.x=t.a AND EXISTS (SELECT * FROM (VALUES (1),(2)) AS s(a) WHERE a>0)"}, expectDifference: true},
		{rule: "DecorrelateJoin", native: only("DecorrelateJoin"), sqls: []string{"SELECT * FROM (VALUES (1)) AS l(a) JOIN LATERAL (SELECT b FROM (VALUES (1)) AS r(b)) AS q ON l.a=q.b"}},
		{rule: "TryRemapJoinOuterColsLeft", native: only("TryRemapJoinOuterColsLeft"), sqls: []string{"SELECT * FROM (VALUES (1)) AS l(a) JOIN LATERAL (SELECT b FROM (VALUES (1)) AS r(b)) AS q ON l.a=q.b WHERE l.a>0"}, expectDifference: true},
		{rule: "TryRemapJoinOuterColsRight", native: only("TryRemapJoinOuterColsRight"), sqls: []string{"SELECT * FROM (VALUES (1)) AS l(a) JOIN LATERAL (SELECT b FROM (VALUES (1)) AS r(b)) AS q ON l.a=q.b WHERE q.b>0"}, expectDifference: true},
		{rule: "EliminateJoinNoColsLeft", native: only("EliminateJoinNoColsLeft"), sqls: []string{"SELECT r.x FROM (SELECT FROM (VALUES (TRUE)) AS v(flag)) AS z JOIN (VALUES (1)) AS r(x) ON r.x=1"}},
		{rule: "EliminateJoinNoColsRight", native: only("EliminateJoinNoColsRight"), sqls: []string{"SELECT l.x FROM (VALUES (1)) AS l(x) JOIN (SELECT FROM (VALUES (TRUE)) AS v(flag)) AS z ON l.x=1"}},
		{rule: "AssociateLimitJoinsLeft", native: only("AssociateLimitJoinsLeft"), sqls: []string{"SELECT * FROM ((VALUES (1)) AS a(x) LEFT JOIN (VALUES (1)) AS b(y) ON a.x=b.y) JOIN (VALUES (1)) AS c(z) ON a.x=c.z"}, expectDifference: true},
		{rule: "AssociateLimitJoinsRight", native: only("AssociateLimitJoinsRight"), sqls: []string{"SELECT * FROM (VALUES (1)) AS c(z) JOIN ((VALUES (1)) AS a(x) LEFT JOIN (VALUES (1)) AS b(y) ON a.x=b.y) ON a.x=c.z"}, expectDifference: true},
		{rule: "LeftAssociateJoinsLeft", native: only("LeftAssociateJoinsLeft"), sqls: []string{"SELECT * FROM ((VALUES (1)) AS a(x) CROSS JOIN (VALUES (1)) AS b(y)) JOIN (VALUES (1)) AS c(z) ON a.x=c.z AND b.y=c.z"}, expectDifference: true},
		{rule: "LeftAssociateJoinsRight", native: only("LeftAssociateJoinsRight"), sqls: []string{"SELECT * FROM ((VALUES (1)) AS a(x) CROSS JOIN (VALUES (1)) AS b(y)) JOIN (VALUES (1)) AS c(z) ON a.x=c.z AND b.y=c.z"}, expectDifference: true},
		{rule: "RightAssociateJoinsLeft", native: only("RightAssociateJoinsLeft"), sqls: []string{"SELECT * FROM (VALUES (1)) AS a(x) JOIN ((VALUES (1)) AS b(y) CROSS JOIN (VALUES (1)) AS c(z)) ON a.x=b.y AND b.y=c.z"}, expectDifference: true},
		{rule: "RightAssociateJoinsRight", native: only("RightAssociateJoinsRight"), sqls: []string{"SELECT * FROM (VALUES (1)) AS a(x) JOIN ((VALUES (1)) AS b(y) CROSS JOIN (VALUES (1)) AS c(z)) ON a.x=c.z AND a.x=b.y"}, expectDifference: true},
		{rule: "TryDecorrelateInnerJoin", native: only("TryDecorrelateInnerJoin"), sqls: []string{"SELECT * FROM (VALUES (1)) AS l(a) JOIN ((VALUES (1)) AS i(b) JOIN (VALUES (1)) AS r(c) ON b=c) ON a=b"}, expectDifference: true},
		{rule: "TryDecorrelateInnerLeftJoin", native: only("TryDecorrelateInnerLeftJoin"), sqls: []string{"SELECT * FROM (VALUES (1)) AS l(a) JOIN ((VALUES (1)) AS i(b) LEFT JOIN (VALUES (1)) AS r(c) ON b=c) ON a=b"}, expectDifference: true},
		{rule: "TryDecorrelateProjectSet", native: only("TryDecorrelateProjectSet"), sqls: []string{"SELECT * FROM (VALUES (1)) AS l(a) JOIN ((VALUES (1)) AS i(b) JOIN (VALUES (1)) AS r(c) ON b=c) ON a=b"}, expectDifference: true},
		{rule: "TryDecorrelateProjectInnerJoin", native: only("TryDecorrelateProjectInnerJoin"), sqls: []string{"SELECT * FROM (VALUES (1)) AS l(a) LEFT JOIN (SELECT b,b+c AS z FROM (VALUES (1)) AS i(b) JOIN (VALUES (1)) AS r(c) ON b=c) AS q ON a=z"}, expectDifference: true},
		{rule: "InlineJoinConstantsLeft", native: only("InlineJoinConstantsLeft"), sqls: []string{"SELECT * FROM ((VALUES (1),(2)) AS s(x) JOIN (VALUES (1)) AS c(v) ON s.x=c.v) JOIN (VALUES (1),(3)) AS r(a) ON s.x=r.a"}, expectDifference: true},
		{rule: "InlineJoinConstantsRight", native: only("InlineJoinConstantsRight"), sqls: []string{"SELECT * FROM (VALUES (1),(3)) AS l(a) JOIN ((VALUES (1),(2)) AS s(x) JOIN (VALUES (1)) AS c(v) ON s.x=c.v) ON l.a=s.x"}, expectDifference: true},
		{rule: "MergeProjectWithValues", native: only("MergeProjectWithValues"), sqls: []string{"SELECT a,3 AS c FROM (VALUES (1,2)) AS v(a,b)"}},
		{rule: "ProjectInnerJoinValues", native: only("ProjectInnerJoinValues"), sqls: []string{"SELECT * FROM (VALUES (9)) AS l(a) JOIN (VALUES (1,2)) AS r(b,c) ON a=b"}},
		{rule: "PruneValuesCols", native: only("PruneValuesCols"), sqls: []string{"SELECT a+5 AS z,b FROM (VALUES (1,2,3)) AS v(a,b,c)"}},
		{rule: "ConvertGroupByToDistinct", native: only("ConvertGroupByToDistinct"), sqls: []string{"SELECT a,b FROM (VALUES (1,2),(3,4)) AS v(a,b) GROUP BY a,b"}, expectDifference: true},
		{rule: "FoldIsNullProject", native: only("FoldIsNullProject"), sqls: []string{"SELECT a IS NULL AS z, b FROM (VALUES (1, 2), (3, 4)) AS v(a, b)"}},
		{rule: "FoldNullUnary", native: only("FoldNullUnary"), sqls: []string{"SELECT -CAST(NULL AS INT) AS z FROM u"}, expectDifference: true},
		{rule: "RejectNullsProject", native: only("RejectNullsProject"), sqls: []string{"SELECT z, a FROM (SELECT -a AS z, a FROM (VALUES (NULL::INT), (1)) AS v(a)) WHERE z IS NOT NULL"}, expectDifference: true},
		{rule: "FoldIsNull", native: only("FoldIsNull"), sqls: []string{"SELECT x FROM u WHERE NULL IS NULL"}, expectDifference: true},
		{rule: "FoldIsNotNull", native: only("FoldIsNotNull"), sqls: []string{"SELECT x FROM u WHERE NULL IS NOT NULL"}},
		{rule: "FoldNotNull", native: only("FoldNotNull"), sqls: []string{"SELECT x FROM u WHERE NOT NULL"}, expectDifference: true},
		{rule: "FoldNullAndOr", native: only("FoldNullAndOr"), sqls: []string{"SELECT x FROM u WHERE NULL AND NULL"}, expectDifference: true},
		{rule: "FoldNullTupleIsTupleNull", native: only("FoldNullTupleIsTupleNull"), sqls: []string{"SELECT x FROM u WHERE NULL IS NULL AND NULL IS NULL"}, expectDifference: true},
		{rule: "FoldNullTupleIsTupleNotNull", native: only("FoldNullTupleIsTupleNotNull"), sqls: []string{"SELECT x FROM u WHERE NULL IS NOT NULL AND x IS NOT NULL"}, expectDifference: true},
		{rule: "SimplifySameVarEqualities", native: only("SimplifySameVarEqualities"), sqls: []string{"SELECT x FROM u WHERE x = x"}},
		{rule: "SimplifySameVarInequalities", native: only("SimplifySameVarInequalities"), sqls: []string{"SELECT x FROM u WHERE x <> x"}, expectDifference: true},
		{rule: "SimplifySelectFilters", native: only("SimplifySelectFilters"), sqls: []string{"SELECT x FROM u WHERE x > 0 OR NULL"}, expectDifference: true},
		{rule: "SimplifyInSingleElement", native: only("SimplifyInSingleElement"), sqls: []string{"SELECT a,b FROM (VALUES (1, 2), (3, 4)) AS v(a,b) WHERE a IN (b)"}},
		{rule: "SimplifyNotInSingleElement", native: only("SimplifyNotInSingleElement"), sqls: []string{"SELECT a,b FROM (VALUES (1, 2), (3, 4)) AS v(a,b) WHERE a NOT IN (b)"}},
		{rule: "NormalizeInConst", native: only("NormalizeInConst"), sqls: []string{"SELECT x FROM (VALUES (1, 2, 3), (4, 5, 6)) AS v(x,a,b) WHERE x=a OR x=b OR x=a OR x=b"}, expectDifference: true},
		{rule: "NegateAnd", native: only("NegateAnd"), sqls: []string{"SELECT x FROM u WHERE NOT (x > 0 AND y < 5)"}},
		{rule: "NormalizeNestedAnds", native: only("NormalizeNestedAnds"), sqls: []string{"SELECT x FROM u WHERE x > 0 AND (y < 5 AND x <> 1)"}},
		{rule: "ExtractRedundantConjunct", native: only("ExtractRedundantConjunct"), sqls: []string{"SELECT x FROM u WHERE (x > 0 AND y < 5) OR (x > 0 AND x <> 1)"}, expectDifference: true},
		{rule: "DeduplicateSelectFilters", native: only("DeduplicateSelectFilters"), sqls: []string{"SELECT * FROM (SELECT * FROM (SELECT * FROM (VALUES (1, 2), (3, 4)) AS v(x,y) WHERE x > 0) WHERE y < 5) WHERE x > 0"}, expectDifference: true},
		{rule: "FoldNonNullIsNull", native: only("FoldNonNullIsNull"), sqls: []string{"SELECT x FROM (VALUES (1), (2)) AS v(x) WHERE x IS NULL"}, expectDifference: true},
		{rule: "FoldNonNullIsNotNull", native: only("FoldNonNullIsNotNull"), sqls: []string{"SELECT x FROM (VALUES (1), (2)) AS v(x) WHERE x IS NOT NULL"}, expectDifference: true},
		{rule: "FoldNonNullTupleIsTupleNull", native: only("FoldNonNullTupleIsTupleNull"), sqls: []string{"SELECT a,b FROM (VALUES (1, NULL), (2, 3)) AS v(a,b) WHERE a IS NULL AND b IS NULL"}, expectDifference: true},
		{rule: "FoldNonNullTupleIsTupleNotNull", native: only("FoldNonNullTupleIsTupleNotNull"), sqls: []string{"SELECT a,b FROM (VALUES (1, 2), (3, 4)) AS v(a,b) WHERE a IS NOT NULL AND b IS NOT NULL"}, expectDifference: true},
		{rule: "SimplifyIsNullCondition", native: only("SimplifyIsNullCondition"), sqls: []string{"SELECT a,b FROM (SELECT a,b FROM (VALUES (1, 2), (3, 4)) AS v(a,b) WHERE b > 0) AS q WHERE a IS NULL"}, expectDifference: true},
		{rule: "RemoveNotNullCondition", native: only("RemoveNotNullCondition"), sqls: []string{"SELECT a,b FROM (SELECT a,b FROM (VALUES (1, 2), (3, 4)) AS v(a,b) WHERE b > 0) AS q WHERE a IS NOT NULL"}, expectDifference: true},
		{rule: "RejectNullsLeftJoin", native: only("RejectNullsLeftJoin"), sqls: []string{"SELECT * FROM (VALUES (1), (2)) AS u(x) LEFT JOIN (VALUES (1), (2)) AS t(a) ON u.x=t.a WHERE t.a IS NOT NULL"}},
		{rule: "RejectNullsRightJoin", native: only("RejectNullsRightJoin"), sqls: []string{"SELECT * FROM (VALUES (1), (2)) AS u(x) FULL JOIN (VALUES (1), (2)) AS t(a) ON u.x=t.a WHERE u.x IS NOT NULL"}},
		{rule: "RejectNullsUnderJoinLeft", native: only("RejectNullsUnderJoinLeft"), sqls: []string{"SELECT * FROM (VALUES (1), (2)) AS u(x) JOIN (VALUES (1), (2)) AS t(a) ON u.x=t.a AND u.x IS NOT NULL"}, expectDifference: true},
		{rule: "RejectNullsUnderJoinRight", native: only("RejectNullsUnderJoinRight"), sqls: []string{"SELECT * FROM (VALUES (1), (2)) AS u(x) JOIN (VALUES (1), (2)) AS t(a) ON u.x=t.a AND t.a IS NOT NULL"}, expectDifference: true},
		{rule: "RemoveJoinNotNullCondition", native: only("RemoveJoinNotNullCondition"), sqls: []string{"SELECT * FROM (VALUES (1), (2)) AS u(x) LEFT JOIN (VALUES (1), (2)) AS t(a) ON u.x IS NOT NULL AND u.x=t.a"}, expectDifference: true},
		{rule: "SimplifyJoinNotNullEquality", native: only("SimplifyJoinNotNullEquality"), sqls: []string{"SELECT * FROM (VALUES (1), (2)) AS u(x) JOIN (VALUES (1), (2)) AS t(a) ON (u.x=t.a) IS NOT FALSE AND u.x>0"}, expectDifference: true},
		{rule: "SimplifyAndTrue", native: only("SimplifyAndTrue"), sqls: []string{"SELECT x FROM u WHERE x > 0 AND TRUE"}},
		{rule: "SimplifyTrueAnd", native: only("SimplifyTrueAnd"), sqls: []string{"SELECT x FROM u WHERE TRUE AND x > 0"}},
		{rule: "FoldBinary", native: only("FoldBinary"), sqls: []string{"SELECT x FROM u WHERE TRUE AND FALSE"}, expectDifference: true},
		{rule: "SimplifyJoinFilters", native: only("SimplifyJoinFilters"), sqls: []string{"SELECT * FROM t JOIN u ON t.a = u.x AND TRUE"}},
		{rule: "SimplifyRange", native: only("SimplifyRange"), sqls: []string{"SELECT x FROM u WHERE x > 0 AND TRUE"}, expectDifference: true},
		{rule: "DetectJoinContradiction", native: only("DetectJoinContradiction"), sqls: []string{"SELECT * FROM (VALUES (1)) AS l(a) JOIN (VALUES (1)) AS r(b) ON FALSE AND a = b"}},
		{rule: "SimplifyAndFalse", native: only("SimplifyAndFalse"), sqls: []string{"SELECT x FROM u WHERE x > 0 AND FALSE"}},
		{rule: "SimplifyFalseAnd", native: only("SimplifyFalseAnd"), sqls: []string{"SELECT x FROM u WHERE FALSE AND x > 0"}},
		{rule: "SortFiltersInJoin", native: only("SortFiltersInJoin"), sqls: []string{"SELECT * FROM t JOIN u ON t.a = u.x AND t.b > 0"}, expectDifference: true},
		{rule: "MapEqualityIntoJoinLeftAndRight", native: only("MapEqualityIntoJoinLeftAndRight"), sqls: []string{"SELECT * FROM (VALUES (1, 2)) AS a(x,y) JOIN (VALUES (1)) AS b(z) ON a.x=b.z AND b.z=a.y"}, expectDifference: true},
		{rule: "MapFilterIntoJoinLeft", native: only("MapFilterIntoJoinLeft"), sqls: []string{"SELECT * FROM (VALUES (1, 2)) AS a(x,y) JOIN (VALUES (1)) AS b(z) ON a.x=b.z AND b.z=a.y"}, expectDifference: true},
		{rule: "MapFilterIntoJoinRight", native: only("MapFilterIntoJoinRight"), sqls: []string{"SELECT * FROM (VALUES (1)) AS a(x) JOIN (VALUES (1)) AS b(y) ON a.x=b.y AND a.x>0"}, expectDifference: true},
		{rule: "PushFilterIntoJoinLeft", native: only("PushFilterIntoJoinLeft"), sqls: []string{"SELECT * FROM (VALUES (1)) AS l(a) JOIN (VALUES (1)) AS r(b) ON l.a>0 AND l.a=r.b"}, expectDifference: true},
		{rule: "PushFilterIntoJoinRight", native: only("PushFilterIntoJoinRight"), sqls: []string{"SELECT * FROM (VALUES (1)) AS l(a) JOIN (VALUES (1)) AS r(b) ON r.b>0 AND l.a=r.b"}, expectDifference: true},
		{rule: "PushFilterIntoJoinLeftAndRight", native: only("PushFilterIntoJoinLeftAndRight"), sqls: []string{"SELECT * FROM (VALUES (1, 2)) AS l(a,b) JOIN (VALUES (1, 2)) AS r(c,d) ON l.a=r.c AND l.b=r.d AND l.a=r.d"}, expectDifference: true},
		{rule: "InlineSelectVirtualColumns", native: only("InlineSelectVirtualColumns"), sqls: []string{"SELECT * FROM (SELECT x+y AS f, x*y AS g, x, y FROM (VALUES (1,2),(3,4)) AS v(x,y)) AS q WHERE f>5 AND g<10"}, expectDifference: true},
		{rule: "PushSelectIntoInlinableProject", native: only("PushSelectIntoInlinableProject"), sqls: []string{"SELECT * FROM (SELECT x+y AS f, x*y AS g, x, y FROM (VALUES (1,2),(3,4)) AS v(x,y)) AS q WHERE f>5 AND g<10"}, expectDifference: true},
		{rule: "PushSelectIntoProject", native: only("PushSelectIntoProject"), sqls: []string{"SELECT * FROM (SELECT x+1 AS f, x FROM (VALUES (1)) AS v(x)) AS q WHERE x>0 AND f<10"}, expectDifference: true},
		{rule: "PushSelectIntoGroupBy", native: only("PushSelectIntoGroupBy"), sqls: []string{"SELECT x, count(y) FROM (VALUES (1, 2)) AS v(x,y) GROUP BY x HAVING x>0 AND x<10"}, expectDifference: true},
		{rule: "PushSelectIntoUnorderedDistinctOn", native: only("PushSelectIntoUnorderedDistinctOn"), sqls: []string{"SELECT a,b FROM (VALUES (1, 2)) AS v(a,b) GROUP BY a,b HAVING a>0 AND b<10"}, expectDifference: true},
		{rule: "PushSelectIntoJoinLeft", native: only("PushSelectIntoJoinLeft"), sqls: []string{"SELECT * FROM (VALUES (1)) AS l(a) LEFT JOIN (VALUES (1)) AS r(b) ON a=b WHERE a>0 AND a<>b"}, expectDifference: true},
		{rule: "PushSelectIntoProjectSet", native: only("PushSelectIntoProjectSet"), sqls: []string{"SELECT * FROM (VALUES (1)) AS l(a) JOIN (VALUES (1)) AS r(b) ON a=b WHERE a>0 AND b<10"}, expectDifference: true},
		{rule: "FoldComparisonWithAny", native: only("FoldComparisonWithAny"),
			sqls:             []string{"SELECT x FROM u WHERE (TRUE = FALSE) OR (TRUE = TRUE)"},
			expectDifference: true,
		},
		{rule: "NegateOr", native: only("NegateOr"),
			sqls:             []string{"SELECT x FROM u WHERE NOT ((x > 0) OR (y > 0))"},
			expectDifference: true,
		},
		{rule: "SimplifyFalseOr", native: only("SimplifyFalseOr"), sqls: []string{"SELECT x FROM u WHERE FALSE OR x > 0"}},
		{rule: "SimplifyOrFalse", native: only("SimplifyOrFalse"), sqls: []string{"SELECT x FROM u WHERE x > 0 OR FALSE"}},
		{rule: "SimplifyOrTrue", native: only("SimplifyOrTrue"),
			sqls:             []string{"SELECT x FROM u WHERE x > 0 OR TRUE"},
			expectDifference: true,
		},
		{rule: "SimplifyTrueOr", native: only("SimplifyTrueOr"),
			sqls:             []string{"SELECT x FROM u WHERE TRUE OR x > 0"},
			expectDifference: true,
		},
		{rule: "EliminateNot", native: only("EliminateNot"), sqls: []string{"SELECT x FROM u WHERE NOT NOT (x > 0)"}},
		{rule: "FoldComparison", native: only("FoldComparison"), sqls: []string{"SELECT x FROM u WHERE TRUE = FALSE"}},
		{rule: "FoldEqFalse", native: only("FoldEqFalse"), sqls: []string{"SELECT x FROM u WHERE (x > 0) = FALSE"}},
		{rule: "FoldEqTrue", native: only("FoldEqTrue"), sqls: []string{"SELECT x FROM u WHERE (x > 0) = TRUE"}},
		{rule: "FoldNeFalse", native: only("FoldNeFalse"), sqls: []string{"SELECT x FROM u WHERE (x > 0) <> FALSE"}},
		{rule: "FoldNeTrue", native: only("FoldNeTrue"), sqls: []string{"SELECT x FROM u WHERE (x > 0) <> TRUE"}},
		{rule: "FoldNotFalse", native: only("FoldNotFalse"), sqls: []string{"SELECT x FROM u WHERE NOT FALSE"},
			expectDifference: true,
		},
		{rule: "FoldNotTrue", native: only("FoldNotTrue"), sqls: []string{"SELECT x FROM u WHERE NOT TRUE"}},
		{rule: "FoldUnary", native: only("FoldUnary"), sqls: []string{"SELECT x FROM u WHERE NOT TRUE"},
			expectDifference: true,
		},
		{rule: "NegateComparison", native: only("NegateComparison"), sqls: []string{"SELECT a, b FROM (VALUES (1)) AS l(a), (VALUES (2)) AS r(b) WHERE NOT (a = b)"}},
		{
			rule: "CommuteConstInequality", native: only("CommuteConstInequality"),
			sqls:             []string{"SELECT a, b FROM (VALUES (1)) AS l(a), (VALUES (2)) AS r(b) WHERE a < b"},
			expectDifference: true,
		},
		{
			rule: "CommuteVarInequality", native: only("CommuteVarInequality"),
			sqls:             []string{"SELECT a, b FROM (VALUES (1)) AS l(a), (VALUES (2)) AS r(b) WHERE a <= b"},
			expectDifference: true,
		},
		{
			rule: "CommuteVar", native: only("CommuteVar"),
			sqls: []string{"SELECT x FROM u WHERE x + 1 = x"},
		},
		{
			rule: "CommuteConst", native: only("CommuteConst"),
			sqls:             []string{"SELECT a, b FROM (VALUES (1)) AS l(a), (VALUES (2)) AS r(b) WHERE a = b"},
			expectDifference: true,
		},
		{
			rule: "InlineConstVar", native: only("InlineConstVar"),
			sqls:             []string{"SELECT l.a FROM (VALUES (1), (2)) AS l(a) JOIN (VALUES (1)) AS r(b) ON l.a = r.b WHERE l.a > 0"},
			expectDifference: true,
		},
		{
			rule: "InlineSelectConstants", native: only("InlineSelectConstants"),
			sqls:             []string{"SELECT l.a FROM (VALUES (1), (2)) AS l(a) JOIN (VALUES (1)) AS r(b) ON l.a = r.b WHERE l.a > 0"},
			expectDifference: true,
		},
		{
			rule: "InlineProjectConstants", native: only("InlineProjectConstants"),
			sqls:             []string{"SELECT l.a + 1 FROM (VALUES (1), (2)) AS l(a) JOIN (VALUES (1)) AS r(b) ON l.a = r.b"},
			expectDifference: true,
		},
		{
			rule: "PushSelectCondLeftIntoJoinLeftAndRight", native: only("PushSelectCondLeftIntoJoinLeftAndRight"),
			sqls:             []string{"SELECT l.a FROM (VALUES (1), (2)) AS l(a) LEFT JOIN (VALUES (1), (2)) AS r(b) ON l.a = r.b WHERE l.a > 0"},
			expectDifference: true,
		},
		{
			rule: "SimplifyIntersectLeft", native: only("SimplifyIntersectLeft"),
			sqls:             []string{"SELECT x FROM u INTERSECT SELECT a FROM t"},
			expectDifference: true,
		},
		{
			rule: "SimplifyIntersectRight", native: only("SimplifyIntersectRight"),
			sqls:             []string{"SELECT x FROM u INTERSECT SELECT a FROM t"},
			expectDifference: true,
		},
		{
			rule: "EliminateDistinct", native: only("EliminateDistinct"),
			sqls:             []string{"SELECT x FROM u GROUP BY x"},
			expectDifference: true,
		},
		{
			rule: "EliminateUpsertDistinct", native: only("EliminateUpsertDistinct"),
			sqls:             []string{"SELECT x FROM u GROUP BY x"},
			expectDifference: true,
		},
		{
			rule: "EliminateAggDistinct", native: only("EliminateAggDistinct"),
			sqls:             []string{"SELECT x, count(DISTINCT x) FROM u GROUP BY x"},
			expectDifference: true,
		},
		{
			rule: "EliminateAggDistinctForKeys", native: only("EliminateAggDistinctForKeys"),
			sqls: []string{"SELECT x, count(DISTINCT x) FROM u GROUP BY x"},
		},
		{
			rule: "EliminateAggFilteredDistinctForKeys", native: only("EliminateAggFilteredDistinctForKeys"),
			sqls:             []string{"SELECT x, count(DISTINCT x) FROM u GROUP BY x"},
			expectDifference: true,
		},
		{
			rule: "HoistSelectAboveUnorderedDistinctOn", native: only("HoistSelectAboveUnorderedDistinctOn"),
			sqls:             []string{"SELECT a, b FROM (VALUES (1, 2), (3, 4)) AS v(a, b) WHERE a > 0 GROUP BY a, b"},
			expectDifference: true,
		},
		{
			rule:   "EliminateGroupByProject",
			native: only("EliminateGroupByProject"),
			sqls:   []string{"SELECT a FROM (VALUES (1, 2)) AS v(a, b) GROUP BY a"},
		},
		{
			rule:   "PruneAggCols",
			native: only("PruneAggCols"),
			sqls:   []string{"SELECT a, cnt FROM (SELECT a, count(b) AS cnt, count(c) AS unused FROM t GROUP BY a) AS q"},
		},
		{
			rule:             "CommuteRightJoin",
			native:           only("CommuteRightJoin"),
			helper:           func(r opt.RuleName) bool { return strings.HasPrefix(r.String(), "Prune") },
			sqls:             []string{"SELECT t.a, u.x FROM t RIGHT JOIN u ON t.a = u.x"},
			expectDifference: true,
		},
		{
			rule:   "EliminateProject",
			native: only("EliminateProject"),
			helper: func(r opt.RuleName) bool { return strings.HasPrefix(r.String(), "Prune") },
			sqls:   []string{"SELECT x, y FROM u"},
		},
		{
			rule:             "PruneScanCols",
			native:           only("PruneScanCols"),
			sqls:             []string{"SELECT a + b FROM (VALUES (1, 2, 3)) AS v(a, b, c)"},
			expectDifference: true,
		},
		{
			rule:   "PruneUnionAllCols",
			native: only("PruneUnionAllCols"),
			sqls:   []string{"SELECT q.a + q.c FROM (SELECT a, b, c FROM t UNION ALL SELECT a, b, c FROM t) AS q"},
		},
		{
			rule:             "PushFilterIntoSetOp",
			native:           only("PushFilterIntoSetOp"),
			sqls:             []string{"SELECT a FROM (SELECT a FROM t INTERSECT SELECT x FROM u) AS q WHERE a > 0"},
			expectDifference: true,
		},
		{
			rule:   "EliminateSelect",
			native: only("EliminateSelect"),
			helper: only("SimplifySelectFilters"),
			sqls:   []string{"SELECT a FROM t WHERE TRUE"},
		},
		{
			rule:             "InlineProjectInProject",
			native:           only("InlineProjectInProject"),
			helper:           prune,
			sqls:             []string{"SELECT x2 * 2 AS z, x2 FROM (SELECT x + 1 AS x2 FROM u)"},
			expectDifference: true,
		},
		{
			rule:   "MergeProjects",
			native: only("MergeProjects"),
			sqls:   []string{"SELECT x * 2 FROM (SELECT x, x + 1 AS b FROM u)"},
		},
		{
			rule:   "HoistJoinProjectLeft",
			native: only("HoistJoinProjectLeft"),
			helper: func(r opt.RuleName) bool { return strings.HasPrefix(r.String(), "Prune") },
			sqls:   []string{"SELECT q.y, q.x, t.a FROM (SELECT y, x FROM u) AS q JOIN t ON q.y = t.a"},
		},
		{
			rule:   "HoistJoinProjectRight",
			native: only("HoistJoinProjectRight"),
			helper: func(r opt.RuleName) bool { return strings.HasPrefix(r.String(), "Prune") },
			sqls:   []string{"SELECT t.a, q.y, q.x FROM t LEFT JOIN (SELECT y, x FROM u) AS q ON t.a = q.y"},
		},
		{
			rule:             "TryDecorrelateSelect",
			native:           only("TryDecorrelateSelect"),
			helper:           func(r opt.RuleName) bool { return strings.HasPrefix(r.String(), "Prune") || r == opt.EliminateProject },
			sqls:             []string{"SELECT t.a, q.x FROM t JOIN (SELECT x FROM u WHERE x > 0) AS q ON t.a = q.x"},
			expectDifference: true,
		},
		{
			rule:             "TryDecorrelateProject",
			native:           only("TryDecorrelateProject"),
			helper:           func(r opt.RuleName) bool { return strings.HasPrefix(r.String(), "Prune") },
			sqls:             []string{"SELECT t.a, q.y, q.x FROM t JOIN (SELECT y, x FROM u) AS q ON t.a = q.y"},
			expectDifference: true,
		},
		{
			rule:             "TryDecorrelateProjectSelect",
			native:           only("TryDecorrelateProjectSelect"),
			helper:           func(r opt.RuleName) bool { return strings.HasPrefix(r.String(), "Prune") || r == opt.EliminateProject },
			sqls:             []string{"SELECT t.a, q.y FROM t LEFT JOIN (SELECT y FROM u WHERE x > 0) AS q ON t.a = q.y"},
			expectDifference: true,
		},
		{
			rule:   "MergeSelects",
			native: only("MergeSelects"),
			helper: prune,
			sqls: []string{
				"SELECT * FROM (SELECT * FROM t WHERE a > 1) WHERE b < 5",
				"SELECT * FROM (SELECT * FROM t WHERE a > 1 AND c = 7) WHERE b < 5 AND a <> 3",
			},
		},
		{
			rule:   "MergeSelectInnerJoin",
			native: only("MergeSelectInnerJoin"),
			sqls: []string{
				"SELECT * FROM t, u WHERE t.a = u.x AND t.b > u.y",
				"SELECT * FROM t JOIN u ON t.a = u.x WHERE t.c > 0 AND u.y < 9",
				"SELECT * FROM t, u WHERE t.b > 1",
			},
		},
		{
			rule:   "FilterProjectTranspose",
			native: only("PushSelectIntoInlinableProject"),
			sqls: []string{
				"SELECT * FROM (SELECT a + b AS s FROM t) WHERE s > 10",
				"SELECT * FROM (SELECT a * c AS s FROM t) WHERE s > 10 AND s <> 12",
			},
		},
	}
	for _, tc := range cases {
		helper := tc.helper
		if helper == nil {
			helper = func(opt.RuleName) bool { return false }
		}
		for _, sql := range tc.sqls {
			got, fired := planFor(t, sql, helper, tc.rule)
			if len(fired) == 0 {
				t.Errorf("%s did not fire on: %s\n%s", tc.rule, sql, got)
				continue
			}
			want, _ := planFor(t, sql, either(helper, tc.native))
			if tc.expectDifference && got == want {
				t.Errorf("%s unexpectedly matched the native plan on: %s\n%s", tc.rule, sql, got)
			} else if !tc.expectDifference && got != want {
				t.Errorf("%s differs from the native rule on: %s\nRuleScript:\n%s\nnative:\n%s", tc.rule, sql, got, want)
			}
		}
	}
	t.Run("PushLeakproofFiltersIntoPermeableBarrier", func(t *testing.T) {
		e := newEnv(t)
		installed := rulescript.Install(&e.f, loadRule(t, "PushLeakproofFiltersIntoPermeableBarrier"))
		nativeMatches := 0
		e.f.NotifyOnMatchedRule(func(r opt.RuleName) bool {
			if r == opt.ExternalNormalizeRule {
				return true
			}
			if r.String() == "PushLeakproofFiltersIntoPermeableBarrier" {
				nativeMatches++
				return true
			}
			return false
		})
		s, cols := e.scan("u", 1)
		project := e.f.ConstructProject(s, nil, opt.MakeColSet(cols[0]))
		leakproof := e.f.ConstructGt(e.v(cols[0]), e.num(0))
		remaining := e.f.ConstructLt(e.v(cols[0]), e.num(10))
		_ = e.f.ConstructSelect(project, e.filters(leakproof, remaining))
		if len(installed.Applied) == 0 || nativeMatches != 0 {
			t.Fatalf("Project barrier port should fire while native Barrier rule should not: RuleScript %v, native matches %d", installed.Applied, nativeMatches)
		}
	})
	t.Run("EliminateAntiJoin", func(t *testing.T) {
		e := newEnv(t)
		l, lc := e.scan("t", 1)
		rc := e.f.Metadata().AddColumn("empty", types.Int)
		r := e.empty(rc)
		on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc)))
		before := e.f.ConstructAntiJoin(l, r, on, &memo.JoinPrivate{})
		generated, ok := loadRule(t, "EliminateAntiJoin").Apply(&e.f, before)
		if !ok {
			t.Fatal("generated rule did not fire")
		}
		e.f.NotifyOnMatchedRule(only("EliminateAntiJoin"))
		native := e.f.ConstructAntiJoin(l, r, on, &memo.JoinPrivate{})
		if e.format(generated) != e.format(native) {
			t.Fatalf("generated plan differs from native:\n%s\nnative:\n%s", e.format(generated), e.format(native))
		}
	})
	t.Run("SimplifyIsCondition", func(t *testing.T) {
		e := newEnv(t)
		s, cols := e.scan("u", 2)
		inner := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.v(cols[1]), e.num(1))))
		before := e.f.ConstructSelect(inner, e.filters(e.f.ConstructIs(e.v(cols[0]), e.v(cols[1]))))
		generated, ok := loadRule(t, "SimplifyIsCondition").Apply(&e.f, before)
		if !ok {
			t.Fatal("generated rule did not fire")
		}
		e.f.NotifyOnMatchedRule(only("SimplifyIsCondition"))
		native := e.f.ConstructSelect(inner, e.filters(e.f.ConstructIs(e.v(cols[0]), e.v(cols[1]))))
		if e.format(generated) != e.format(native) {
			t.Fatalf("generated plan differs from native:\n%s\nnative:\n%s", e.format(generated), e.format(native))
		}
	})
	t.Run("FoldNullComparisonRight", func(t *testing.T) {
		e := newEnv(t)
		s, cols := e.scan("u", 1)
		left := e.f.ConstructGt(e.v(cols[0]), e.num(0))
		nullBool := e.f.ConstructNull(types.Bool)
		before := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(left, nullBool)))
		generated, ok := loadRule(t, "FoldNullComparisonRight").Apply(&e.f, before)
		if !ok {
			t.Fatal("generated rule did not fire")
		}
		nativeMatches := 0
		e.f.NotifyOnMatchedRule(func(r opt.RuleName) bool {
			if r.String() == "FoldNullComparisonRight" {
				nativeMatches++
				return true
			}
			return false
		})
		native := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(left, nullBool)))
		if nativeMatches == 0 || e.format(generated) != e.format(native) {
			t.Fatalf("generated plan differs from native, matches %d:\n%s\nnative:\n%s", nativeMatches, e.format(generated), e.format(native))
		}
	})
	t.Run("FoldNullComparisonLeft", func(t *testing.T) {
		e := newEnv(t)
		s, cols := e.scan("u", 1)
		nullInt := e.f.ConstructNull(types.Int)
		before := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(nullInt, e.v(cols[0]))))
		generated, ok := loadRule(t, "FoldNullComparisonLeft").Apply(&e.f, before)
		if !ok {
			t.Fatal("generated rule did not fire")
		}
		nativeMatches := 0
		e.f.NotifyOnMatchedRule(func(r opt.RuleName) bool {
			if r.String() == "FoldNullComparisonLeft" {
				nativeMatches++
				return true
			}
			return false
		})
		native := e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(nullInt, e.v(cols[0]))))
		if nativeMatches == 0 || e.format(generated) != e.format(native) {
			t.Fatalf("generated plan differs from native, matches %d:\n%s\nnative:\n%s", nativeMatches, e.format(generated), e.format(native))
		}
	})
	t.Run("FoldNullInNonEmpty", func(t *testing.T) {
		e := newEnv(t)
		s, cols := e.scan("u", 2)
		nullInt := e.f.ConstructNull(types.Int)
		first := e.f.ConstructEq(nullInt, e.v(cols[0]))
		second := e.f.ConstructEq(nullInt, e.v(cols[1]))
		before := e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(first, second)))
		generated, ok := loadRule(t, "FoldNullInNonEmpty").Apply(&e.f, before)
		if !ok {
			t.Fatal("generated rule did not fire")
		}
		nativeMatches := 0
		e.f.NotifyOnMatchedRule(func(r opt.RuleName) bool {
			if r.String() == "FoldNullInNonEmpty" {
				nativeMatches++
				return true
			}
			return false
		})
		tuple := e.f.ConstructTuple(memo.ScalarListExpr{e.v(cols[0]), e.v(cols[1])},
			types.MakeTuple([]*types.T{types.Int, types.Int}))
		native := e.f.ConstructSelect(s, e.filters(e.f.ConstructIn(nullInt, tuple)))
		if nativeMatches == 0 || e.format(generated) != e.format(native) {
			t.Fatalf("generated plan differs from native, matches %d:\n%s\nnative:\n%s", nativeMatches, e.format(generated), e.format(native))
		}
	})
	t.Run("SimplifyLeftJoinWithZeroRowsRight", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("u", 2)
		rcInt := e.f.Metadata().AddColumn("empty_int", types.Int)
		rcBool := e.f.Metadata().AddColumn("empty_bool", types.Bool)
		right := e.empty(rcInt, rcBool)
		on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rcInt)))
		before := e.f.ConstructLeftJoin(left, right, on, &memo.JoinPrivate{})
		generated, ok := loadRule(t, "SimplifyLeftJoinWithZeroRowsRight").Apply(&e.f, before)
		if !ok {
			t.Fatal("generated rule did not fire")
		}
		e.f.NotifyOnMatchedRule(only("SimplifyLeftJoinWithZeroRowsRight"))
		native := e.f.ConstructLeftJoin(left, right, on, &memo.JoinPrivate{})
		if e.format(generated) != e.format(native) {
			t.Fatalf("generated plan differs from native:\n%s\nnative:\n%s", e.format(generated), e.format(native))
		}
	})
	t.Run("ReduceNotNullGroupingCols", func(t *testing.T) {
		e := newEnv(t)
		s, cols := e.scan("u", 1)
		alias := e.f.Metadata().AddColumn("alias", types.Int)
		project := e.f.ConstructProject(s, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.v(cols[0]), alias),
		}, opt.MakeColSet(cols[0]))
		before := e.f.ConstructGroupBy(project, nil, &memo.GroupingPrivate{
			GroupingCols: opt.MakeColSet(cols[0], alias),
		})
		generated, ok := loadRule(t, "ReduceNotNullGroupingCols").Apply(&e.f, before)
		if !ok {
			t.Fatal("generated rule did not fire")
		}
		nativeMatches := 0
		e.f.NotifyOnMatchedRule(func(r opt.RuleName) bool {
			if r.String() == "ReduceNotNullGroupingCols" {
				nativeMatches++
				return true
			}
			return false
		})
		native := e.f.ConstructGroupBy(project, nil, &memo.GroupingPrivate{
			GroupingCols: opt.MakeColSet(cols[0], alias),
		})
		if nativeMatches != 0 || e.format(generated) == e.format(native) {
			t.Fatalf("expected documented GroupBy/UpsertDistinctOn difference: native matches %d, generated %s, native %s",
				nativeMatches, e.format(generated), e.format(native))
		}
	})
	t.Run("TryDecorrelateSemiJoin", func(t *testing.T) {
		e := newEnv(t)
		l, lc := e.scan("u", 1)
		r, rc := e.scan("t", 1)
		on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
		before := e.f.ConstructSemiJoinApply(l, r, on, &memo.JoinPrivate{})
		generated, ok := loadRule(t, "TryDecorrelateSemiJoin").Apply(&e.f, before)
		if !ok {
			t.Fatal("generated rule did not fire")
		}
		nativeMatches := 0
		e.f.NotifyOnMatchedRule(func(name opt.RuleName) bool {
			if name.String() == "TryDecorrelateSemiJoin" {
				nativeMatches++
				return true
			}
			return false
		})
		native := e.f.ConstructSemiJoinApply(l, r, on, &memo.JoinPrivate{})
		if nativeMatches != 0 || e.format(generated) == e.format(native) {
			t.Fatalf("expected RuleScript port to fire without native outer-column guard: matches %d, generated %s, native %s",
				nativeMatches, e.format(generated), e.format(native))
		}
	})
	t.Run("EliminateExistsZeroRows", func(t *testing.T) {
		e := newEnv(t)
		outer, _ := e.scan("u", 1)
		col := e.f.Metadata().AddColumn("empty", types.Int)
		empty := e.empty(col)
		before := e.f.ConstructSelect(outer, e.filters(e.exists(empty)))
		generated, ok := loadRule(t, "EliminateExistsZeroRows").Apply(&e.f, before)
		if !ok {
			t.Fatal("generated rule did not fire")
		}
		nativeMatches := 0
		e.f.NotifyOnMatchedRule(func(name opt.RuleName) bool {
			if name.String() == "EliminateExistsZeroRows" {
				nativeMatches++
				return true
			}
			return false
		})
		native := e.f.ConstructSelect(outer, e.filters(e.exists(empty)))
		if nativeMatches == 0 || e.format(generated) != e.format(native) {
			t.Fatalf("generated plan differs from native, matches %d:\n%s\nnative:\n%s",
				nativeMatches, e.format(generated), e.format(native))
		}
	})
}
