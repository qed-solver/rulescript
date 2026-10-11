package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPushAggDistinctIntoGroupBy(t *testing.T) {
	rule := loadRule(t, "PushAggDistinctIntoGroupBy")
	e := newEnv(t)
	s, cols := e.scan("t", 1)
	k1 := e.f.Metadata().AddColumn("k1", types.Int)
	k2 := e.f.Metadata().AddColumn("k2", types.Int)
	x := e.f.Metadata().AddColumn("x", types.Int)
	fcol := e.f.Metadata().AddColumn("f", types.Int)
	keyItems := memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(1)), k1),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(2)), k2),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(3)), x),
	}
	project := e.f.ConstructProject(s, keyItems, s.Relational().OutputCols)
	distinct := memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructAggDistinct(e.f.ConstructCount(e.v(x))), fcol),
	}
	before := e.f.ConstructGroupBy(project, distinct,
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1, k2)})
	inner := e.f.ConstructGroupBy(project, nil,
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1, k2, x)})
	want := e.f.ConstructGroupBy(inner, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(x)), fcol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1, k2)})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(project, distinct,
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1, x)}))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(project, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(x)), fcol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(k1, k2)}))
}

func TestSQLPushAggDistinctIntoGroupBy(t *testing.T) {
	helper := func(r opt.RuleName) bool {
		return r == opt.EliminateProject || strings.HasPrefix(r.String(), "Prune")
	}
	for _, tc := range []struct {
		sql   string
		fires bool
	}{
		{"SELECT a+1 AS k1, a+2 AS k2, count(DISTINCT a+3) AS f FROM t GROUP BY a+1, a+2", false},
		{"SELECT k1,k2,count(DISTINCT x) FROM (SELECT a+1 AS k1,a+2 AS k2,a+3 AS x FROM t) AS q GROUP BY k1,k2", true},
		{"SELECT k1,k2,count(DISTINCT x) FROM (SELECT a+1 AS k1,a+2 AS k2,a+3 AS x FROM t) AS q GROUP BY 1,2", true},
	} {
		before, bFired := planFor(t, tc.sql, helper)
		after, fired := planFor(t, tc.sql, helper, "PushAggDistinctIntoGroupBy")
		if len(bFired) != 0 || !strings.Contains(before, "agg-distinct") {
			t.Fatalf("unexpected SQL input shape: %v\n%s", bFired, before)
		}
		if tc.fires {
			if strings.Join(fired, ",") != "PushAggDistinctIntoGroupBy" || strings.Contains(after, "agg-distinct") ||
				strings.Count(after, "group-by") != 2 {
				t.Fatalf("DISTINCT pushdown did not rewrite SQL plan: %v\n%s", fired, after)
			}
		} else if len(fired) != 0 || before != after {
			t.Fatalf("rule matched different computed-key order: %v\n%s", fired, after)
		}
	}
}
