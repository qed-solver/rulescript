package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinUnionTranspose(t *testing.T) {
	rule := loadRule(t, "JoinUnionTranspose")
	e := newEnv(t)

	x, xc := e.scan("u", 1)
	y, yc := e.scan("nn", 1)
	o, oc := e.scan("t", 1)
	u := e.f.Metadata().AddColumn("union", e.v(xc[0]).DataType())
	left := e.f.ConstructUnionAll(x, y, &memo.SetPrivate{
		LeftCols: opt.ColList{xc[0]}, RightCols: opt.ColList{yc[0]}, OutCols: opt.ColList{u},
	})
	before := e.f.ConstructInnerJoin(left, o, e.filters(e.f.ConstructGt(e.v(u), e.v(oc[0]))), &memo.JoinPrivate{})
	branch1 := e.f.ConstructInnerJoin(x, o, e.filters(e.f.ConstructGt(e.v(xc[0]), e.v(oc[0]))), &memo.JoinPrivate{})
	branch2 := e.f.ConstructInnerJoin(y, o, e.filters(e.f.ConstructGt(e.v(yc[0]), e.v(oc[0]))), &memo.JoinPrivate{})
	branch2Left := e.f.Metadata().AddColumn("union_y", e.v(yc[0]).DataType())
	branch2Right := e.f.Metadata().AddColumn("union_o", e.v(oc[0]).DataType())
	branch2 = e.f.ConstructProject(branch2, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(yc[0]), branch2Left),
		e.f.ConstructProjectionsItem(e.v(oc[0]), branch2Right),
	}, opt.ColSet{})
	got, ok := rule.Apply(&e.f, before)
	if !ok {
		t.Fatal("JoinUnionTranspose did not apply")
	}
	gotOut := got.(*memo.ProjectExpr).Input.(*memo.UnionAllExpr).SetPrivate.OutCols
	unionOut1, unionOut2 := gotOut[0], gotOut[1]
	want := e.f.ConstructUnionAll(branch1, branch2, &memo.SetPrivate{
		LeftCols: opt.ColList{xc[0], oc[0]}, RightCols: opt.ColList{branch2Left, branch2Right}, OutCols: opt.ColList{unionOut1, unionOut2},
	})
	want = e.f.ConstructProject(want, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(unionOut1), u),
		e.f.ConstructProjectionsItem(e.v(unionOut2), oc[0]),
	}, opt.ColSet{})
	g, _ := alphaComputedColumns(e.format(got))
	w, _ := alphaComputedColumns(e.format(want))
	if g != w {
		t.Fatalf("JoinUnionTranspose produced an unexpected plan\ngot:\n%s\nwant:\n%s", g, w)
	}

	a, ac := e.scan("u", 1)
	b, bc := e.scan("t", 2)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(a, b, e.filters(e.f.ConstructEq(e.v(ac[0]), e.v(bc[0]))), &memo.JoinPrivate{}))
}
