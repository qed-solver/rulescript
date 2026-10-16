package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneMutationReturnCols(t *testing.T) {
	rule := loadRule(t, "PruneMutationReturnCols")
	e := newEnv(t)
	if _, err := e.cat.ExecuteDDL("CREATE TABLE quad (a INT PRIMARY KEY, b INT, c INT, d INT)"); err != nil {
		t.Fatal(err)
	}
	s, cols := e.scan("quad", 4)
	md := e.f.Metadata()
	returned := make([]opt.ColumnID, 4)
	items := make([]memo.ProjectionsItem, 4)
	for i := range items {
		returned[i] = md.AddColumn("returned", types.Int)
		sum := e.f.ConstructPlus(e.v(cols[i]), e.v(cols[(i+1)%4]))
		items[i] = e.f.ConstructProjectionsItem(sum, returned[i])
	}
	z := md.AddColumn("z", types.Int)
	top := func(input memo.RelExpr, first, second opt.ColumnID) memo.RelExpr {
		return e.f.ConstructProject(input, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(first)), z),
		}, opt.MakeColSet(second))
	}
	full := e.f.ConstructProject(s, memo.ProjectionsExpr{items[0], items[1], items[2], items[3]}, opt.ColSet{})
	pruned := e.f.ConstructProject(s, memo.ProjectionsExpr{items[0], items[2], items[3]}, opt.ColSet{})
	e.expectRewrite(rule, top(full, returned[0], returned[3]), top(pruned, returned[0], returned[3]))
	e.expectNoMatch(rule, top(full, returned[0], returned[1]))
}
