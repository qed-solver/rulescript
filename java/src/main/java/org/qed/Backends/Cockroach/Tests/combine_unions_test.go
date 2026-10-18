package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/catconstants"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/tree"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func combineUnionsScan(e *env, table string, col int) (memo.RelExpr, opt.ColumnID) {
	tn := tree.NewTableNameWithSchema("t", catconstants.PublicSchemaName, tree.Name(table))
	tabID := e.f.Metadata().AddTable(e.cat.Table(tn), tn)
	c := tabID.ColumnID(col)
	return e.f.ConstructScan(&memo.ScanPrivate{Table: tabID, Cols: opt.MakeColSet(c)}), c
}

func TestCombineUnions(t *testing.T) {
	rule := loadRule(t, "CombineUnions")
	e := newEnv(t)

	a, ac := combineUnionsScan(e, "t", 1)
	b, bc := combineUnionsScan(e, "u", 1)
	c, cc := combineUnionsScan(e, "t", 2)
	d, dc := combineUnionsScan(e, "nn", 1)

	union := func(all bool, l memo.RelExpr, lc opt.ColumnID, r memo.RelExpr, rc opt.ColumnID) (memo.RelExpr, opt.ColumnID) {
		out := e.f.Metadata().AddColumn("union", types.Int)
		priv := &memo.SetPrivate{
			LeftCols:  opt.ColList{lc},
			RightCols: opt.ColList{rc},
			OutCols:   opt.ColList{out},
		}
		if all {
			return e.f.ConstructUnionAll(l, r, priv), out
		}
		return e.f.ConstructUnion(l, r, priv), out
	}
	distinct := func(input memo.RelExpr, col opt.ColumnID) memo.RelExpr {
		return e.f.ConstructGroupBy(input, memo.AggregationsExpr{}, &memo.GroupingPrivate{
			GroupingCols: opt.MakeColSet(col),
		})
	}

	inner, ic := union(true, b, bc, c, cc)
	innerDistinct := distinct(inner, ic)
	mid, mc := union(true, a, ac, innerDistinct, ic)
	top, tc := union(true, mid, mc, d, dc)
	before := distinct(top, tc)

	left1, l1c := union(true, a, ac, b, bc)
	left2, l2c := union(true, left1, l1c, c, cc)
	left3, l3c := union(true, left2, l2c, d, dc)
	want := distinct(left3, l3c)

	got, ok := rule.Apply(&e.f, before)
	if !ok {
		t.Fatalf("%s did not apply to:\n%s", rule.Name, e.format(before))
	}
	proj, ok := got.(*memo.ProjectExpr)
	if !ok {
		t.Fatalf("expected a rename project over the rewritten plan, got:\n%s", e.format(got))
	}
	if g, w := canonicalColumns(e.format(proj.Input)), canonicalColumns(e.format(want)); g != w {
		t.Fatalf("unexpected plan\ngot:\n%s\nwant:\n%s", g, w)
	}
	if len(proj.Projections) != 1 || proj.Projections[0].Col != tc || !proj.Passthrough.Empty() {
		t.Fatalf("expected a single rename back to column %d, got:\n%s", tc, e.format(got))
	}

	unionDistinctMid, udc := union(false, a, ac, innerDistinct, ic)
	unionDistinctTop, utc := union(true, unionDistinctMid, udc, d, dc)
	e.expectNoMatch(rule, distinct(unionDistinctTop, utc))

	scanInner := distinct(b, bc)
	scanMid, smc := union(true, a, ac, scanInner, bc)
	scanTop, stc := union(true, scanMid, smc, d, dc)
	e.expectNoMatch(rule, distinct(scanTop, stc))

	e.expectNoMatch(rule, top)
}
