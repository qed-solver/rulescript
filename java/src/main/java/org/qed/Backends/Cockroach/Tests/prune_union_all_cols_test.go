package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneUnionAllCols(t *testing.T) {
	rule := loadRule(t, "PruneUnionAllCols")
	e := newEnv(t)
	l, lc := e.scan("t", 3)
	r, rc := e.scan("t", 3)
	outCols := opt.ColList{
		e.f.Metadata().AddColumn("o0", types.Int),
		e.f.Metadata().AddColumn("o1", types.Int),
		e.f.Metadata().AddColumn("o2", types.Int),
	}
	result := e.f.Metadata().AddColumn("result", types.Int)
	private := &memo.SetPrivate{
		LeftCols:  opt.ColList{lc[0], lc[1], lc[2]},
		RightCols: opt.ColList{rc[0], rc[1], rc[2]},
		OutCols:   outCols,
	}
	union := e.f.ConstructUnionAll(l, r, private)
	before := e.f.ConstructProject(union, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(outCols[0]), e.v(outCols[2])), result),
	}, opt.ColSet{})
	lpruned := e.f.ConstructProject(l, nil, opt.MakeColSet(lc[0], lc[2]))
	rpruned := e.f.ConstructProject(r, nil, opt.MakeColSet(rc[0], rc[2]))
	prunedUnion := e.f.ConstructUnionAll(lpruned, rpruned, &memo.SetPrivate{
		LeftCols:  opt.ColList{lc[0], lc[2]},
		RightCols: opt.ColList{rc[0], rc[2]},
		OutCols:   opt.ColList{outCols[0], outCols[2]},
	})
	want := e.f.ConstructProject(prunedUnion, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(outCols[0]), e.v(outCols[2])), result),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(union, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(outCols[0]), e.v(outCols[1])), result),
	}, opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructProject(e.f.ConstructUnion(l, r, private), memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(outCols[0]), e.v(outCols[2])), result),
	}, opt.ColSet{}))
	e.expectNoMatch(rule, union)
}

func TestSQLPruneUnionAllCols(t *testing.T) {
	sql := "SELECT q.a + q.c FROM (SELECT a, b, c FROM t UNION ALL SELECT a, b, c FROM t) AS q"
	before, _ := planFor(t, sql, nil)
	if !strings.Contains(before, "union-all") || !strings.Contains(before, "project") {
		t.Fatalf("expected a project over union all:\n%s", before)
	}
	got, fired := planFor(t, sql, nil, "PruneUnionAllCols")
	if strings.Join(fired, ",") != "PruneUnionAllCols" {
		t.Fatalf("expected the rule to fire, got %v:\n%s", fired, got)
	}
}
